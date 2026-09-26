package com.mannequin.client.render;

import com.mannequin.MannequinMod;
import com.mannequin.entity.MannequinEntity;
import com.mannequin.entity.MannequinPose;
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
import net.minecraft.util.Mth;

public class MannequinModel extends EntityModel<MannequinEntity> {

    public static final ModelLayerLocation LAYER_LOCATION =
        new ModelLayerLocation(MannequinMod.id("mannequin"), "main");

    private static final int TEXTURE_SIZE = 16;
    private static final float PELVIS_DEFAULT_Y = 16.0F;

    private final ModelPart root;
    private final ModelPart pelvis;
    private final ModelPart chest;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;

    public MannequinModel(ModelPart root) {
        this.root = root;
        this.pelvis = root.getChild("pelvis");
        this.rightLeg = pelvis.getChild("right_leg");
        this.rightShin = rightLeg.getChild("right_shin");
        this.leftLeg = pelvis.getChild("left_leg");
        this.leftShin = leftLeg.getChild("left_shin");

        this.chest = pelvis.getChild("chest");
        this.rightArm = chest.getChild("right_arm");
        this.rightForearm = rightArm.getChild("right_forearm");
        this.leftArm = chest.getChild("left_arm");
        this.leftForearm = leftArm.getChild("left_forearm");

        ModelPart neck = chest.getChild("neck");
        this.head = neck.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 骨盆挂在髋部高度
        PartDefinition pelvis = root.addOrReplaceChild(
            "pelvis",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-2.5F, -2.0F, -1.5F, 5.0F, 2.0F, 3.0F),
            PartPose.offset(0.0F, PELVIS_DEFAULT_Y, 0.0F)
        );

        pelvis.addOrReplaceChild(
            "abdomen",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-2.5F, 0.0F, -1.5F, 5.0F, 3.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 0.0F)
        );

        PartDefinition chest = pelvis.addOrReplaceChild(
            "chest",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-3.5F, 0.0F, -2.0F, 7.0F, 4.0F, 4.0F),
            PartPose.offset(0.0F, 3.0F, 0.0F)
        );

        PartDefinition neck = chest.addOrReplaceChild(
            "neck",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-1.0F, 0.0F, -1.0F, 2.0F, 1.0F, 2.0F),
            PartPose.offset(0.0F, 4.0F, 0.0F)
        );

        neck.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-1.8F, 0.0F, -1.8F, 3.6F, 4.0F, 3.6F),
            PartPose.offset(0.0F, 1.0F, 0.0F)
        );

        // 臂部与肘部关节
        PartDefinition rightArm = chest.addOrReplaceChild(
            "right_arm",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-0.75F, -5.0F, -0.75F, 1.5F, 5.0F, 1.5F),
            PartPose.offset(4.0F, 4.0F, 0.0F)
        );
        rightArm.addOrReplaceChild(
            "right_forearm",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-0.75F, -5.0F, -0.75F, 1.5F, 5.0F, 1.5F),
            PartPose.offset(0.0F, -5.0F, 0.0F)
        );

        PartDefinition leftArm = chest.addOrReplaceChild(
            "left_arm",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-0.75F, -5.0F, -0.75F, 1.5F, 5.0F, 1.5F),
            PartPose.offset(-4.0F, 4.0F, 0.0F)
        );
        leftArm.addOrReplaceChild(
            "left_forearm",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-0.75F, -5.0F, -0.75F, 1.5F, 5.0F, 1.5F),
            PartPose.offset(0.0F, -5.0F, 0.0F)
        );

        // 腿部与膝部关节
        PartDefinition rightLeg = pelvis.addOrReplaceChild(
            "right_leg",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offset(1.5F, -2.0F, 0.0F)
        );
        rightLeg.addOrReplaceChild(
            "right_shin",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-0.9F, -6.5F, -0.9F, 1.8F, 6.5F, 1.8F)
                .addBox(-0.9F, -8.0F, -0.9F, 1.8F, 1.5F, 2.8F),
            PartPose.offset(0.0F, -6.0F, 0.0F)
        );

        PartDefinition leftLeg = pelvis.addOrReplaceChild(
            "left_leg",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-1.0F, -6.0F, -1.0F, 2.0F, 6.0F, 2.0F),
            PartPose.offset(-1.5F, -2.0F, 0.0F)
        );
        leftLeg.addOrReplaceChild(
            "left_shin",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-0.9F, -6.5F, -0.9F, 1.8F, 6.5F, 1.8F)
                .addBox(-0.9F, -8.0F, -0.9F, 1.8F, 1.5F, 2.8F),
            PartPose.offset(0.0F, -6.0F, 0.0F)
        );

        return LayerDefinition.create(mesh, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void resetPose() {
        this.pelvis.y = PELVIS_DEFAULT_Y;
        this.pelvis.xRot = 0.0F;
        this.pelvis.yRot = 0.0F;
        this.pelvis.zRot = 0.0F;
        this.chest.xRot = 0.0F;
        this.chest.yRot = 0.0F;
        this.chest.zRot = 0.0F;
        this.head.xRot = 0.0F;
        this.head.yRot = 0.0F;
        this.head.zRot = 0.0F;

        this.rightArm.xRot = 0.0F;
        this.rightArm.yRot = 0.0F;
        this.rightArm.zRot = 0.0F;
        this.rightForearm.xRot = 0.0F;
        this.rightForearm.yRot = 0.0F;
        this.rightForearm.zRot = 0.0F;

        this.leftArm.xRot = 0.0F;
        this.leftArm.yRot = 0.0F;
        this.leftArm.zRot = 0.0F;
        this.leftForearm.xRot = 0.0F;
        this.leftForearm.yRot = 0.0F;
        this.leftForearm.zRot = 0.0F;

        this.rightLeg.xRot = 0.0F;
        this.rightLeg.yRot = 0.0F;
        this.rightLeg.zRot = 0.0F;
        this.rightShin.xRot = 0.0F;
        this.rightShin.yRot = 0.0F;
        this.rightShin.zRot = 0.0F;

        this.leftLeg.xRot = 0.0F;
        this.leftLeg.yRot = 0.0F;
        this.leftLeg.zRot = 0.0F;
        this.leftShin.xRot = 0.0F;
        this.leftShin.yRot = 0.0F;
        this.leftShin.zRot = 0.0F;
    }

    @Override
    public void setupAnim(
        MannequinEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        resetPose();

        float radHeadYaw = netHeadYaw * ((float) Math.PI / 180F);
        float radHeadPitch = headPitch * ((float) Math.PI / 180F);

        MannequinPose pose = entity.getMannequinPose();
        switch (pose) {
            case RUNNING -> {
                // 1. 躯干压低冲刺，胸腔根据奔跑摆臂产生扭转
                this.pelvis.xRot = 0.35F;
                float runCycle = limbSwing * 0.6662F;
                this.pelvis.y =
                    PELVIS_DEFAULT_Y + Math.abs(Mth.sin(runCycle)) * 1.5F; // 奔跑纵向腾空颠簸
                this.chest.yRot = Mth.sin(runCycle) * 0.25F;

                this.head.xRot = radHeadPitch - 0.25F; // 压低视线注视前方
                this.head.yRot = radHeadYaw;

                // 2. 双臂大幅屈肘（跑步手肘常态弯折约 70~90 度），前后交替摆动
                this.rightArm.xRot = -0.5F + Mth.cos(runCycle) * 1.1F;
                this.rightForearm.xRot = -1.2F + Mth.cos(runCycle) * 0.3F; // 手肘弯曲向胸前

                this.leftArm.xRot =
                    -0.5F + Mth.cos(runCycle + (float) Math.PI) * 1.1F;
                this.leftForearm.xRot =
                    -1.2F + Mth.cos(runCycle + (float) Math.PI) * 0.3F;

                // 3. 双腿大幅迈进与后折叠（后蹬腿折叠小腿踢向臀部，前伸腿大步跨出）
                float rLegAngle = Mth.cos(runCycle);
                this.rightLeg.xRot = rLegAngle * 1.2F;
                this.rightShin.xRot = rLegAngle > 0 ? rLegAngle * 1.5F : 0.2F; // 后腿小腿大幅折叠

                float lLegAngle = Mth.cos(runCycle + (float) Math.PI);
                this.leftLeg.xRot = lLegAngle * 1.2F;
                this.leftShin.xRot = lLegAngle > 0 ? lLegAngle * 1.5F : 0.2F;
            }
            case FIGHTING -> {
                // 拳击/近战防守抱架：侧身对敌，重心下沉，小臂折叠护头
                this.pelvis.y = PELVIS_DEFAULT_Y + 1.0F; // 重心微压
                this.pelvis.yRot = -0.35F; // 侧身面对敌人
                this.chest.yRot = 0.15F; // 躯干反向对齐

                this.head.xRot = radHeadPitch + 0.1F;
                this.head.yRot = radHeadYaw + 0.2F; // 头摆正注视对手

                // 双腿前后分立，双膝微屈
                this.rightLeg.xRot = 0.35F;
                this.rightLeg.zRot = 0.1F;
                this.rightShin.xRot = 0.4F; // 后膝微屈稳固重心

                this.leftLeg.xRot = -0.4F;
                this.leftLeg.zRot = -0.1F;
                this.leftShin.xRot = 0.5F; // 前膝微屈准备弹击

                // 右手后手重拳蓄势（护下巴）
                this.rightArm.xRot = -0.7F;
                this.rightArm.yRot = -0.4F;
                this.rightArm.zRot = 0.3F;
                this.rightForearm.xRot = -1.6F; // 小臂完全贴胸护下巴

                // 左手前手探刺防守（立掌/握拳置于前方）
                this.leftArm.xRot = -1.1F;
                this.leftArm.yRot = 0.3F;
                this.leftArm.zRot = -0.2F;
                this.leftForearm.xRot = -1.3F; // 小臂向上竖起防御视野
            }
            case CROUCHING -> {
                // 单膝伏地/刺客潜行：骨盆大幅下压接近地面，单腿前屈，单腿跪地
                this.pelvis.y = PELVIS_DEFAULT_Y + 5.5F; // 骨盆下降贴地
                this.pelvis.xRot = 0.55F; // 躯干前趴压低轮廓
                this.chest.xRot = -0.15F;

                this.head.xRot = radHeadPitch - 0.3F;
                this.head.yRot = radHeadYaw;

                // 右腿深屈支撑
                this.rightLeg.xRot = -1.2F;
                this.rightShin.xRot = 1.6F; // 小腿折回压在身下

                // 左腿向前跨出半跪
                this.leftLeg.xRot = -0.6F;
                this.leftShin.xRot = 1.1F;

                // 右手前伸低垂触地保持平衡
                this.rightArm.xRot = -0.6F;
                this.rightForearm.xRot = -0.5F;

                // 左手收拢于腰腹
                this.leftArm.xRot = 0.2F;
                this.leftArm.zRot = -0.3F;
                this.leftForearm.xRot = -1.4F;
            }
            case AIMING -> {
                // 战术瞄准/施法：身体侧转成一条直线，主臂伸直副臂托举，前后弓步
                this.pelvis.yRot = -0.6F; // 侧身站位，减少受弹面
                this.chest.yRot = -0.15F;

                this.head.xRot = radHeadPitch;
                this.head.yRot = radHeadYaw + 0.75F; // 头部对齐瞄准线

                // 前后弓步站姿稳固后坐力
                this.rightLeg.xRot = 0.3F;
                this.rightShin.xRot = 0.2F;
                this.leftLeg.xRot = -0.35F;
                this.leftShin.xRot = 0.35F;

                // 主手平举指向目标（完全伸直）
                this.rightArm.xRot = -1.57F;
                this.rightArm.yRot = -0.35F;
                this.rightForearm.xRot = 0.0F;

                // 副手托底支撑或搭弓拉弦
                this.leftArm.xRot = -1.2F;
                this.leftArm.yRot = 0.65F;
                this.leftForearm.xRot = -1.7F; // 小臂折向胸前横向支撑
            }
            case FALLEN -> {
                // 受创仰面瘫倒：骨盆直接落到地面高度，四肢自然弯折瘫开
                this.pelvis.y = PELVIS_DEFAULT_Y + 7.5F; // 完全贴紧地面
                this.pelvis.xRot = -1.5F; // 身体平躺
                this.pelvis.zRot = 0.2F; // 略微侧瘫，更加自然

                this.head.xRot = 0.4F; // 头部无力垂在地上
                this.head.yRot = 0.4F;

                // 双臂无力散开在身体两侧，肘部自然松弛
                this.rightArm.xRot = 0.1F;
                this.rightArm.zRot = 1.1F;
                this.rightForearm.xRot = -0.4F;

                this.leftArm.xRot = -0.3F;
                this.leftArm.zRot = -1.2F;
                this.leftForearm.xRot = -0.6F;

                // 双腿一曲一直摊开
                this.rightLeg.xRot = -0.2F;
                this.rightLeg.zRot = 0.4F;
                this.rightShin.xRot = 0.8F; // 右腿膝部拱起

                this.leftLeg.xRot = 0.1F;
                this.leftLeg.zRot = -0.3F;
                this.leftShin.xRot = 0.2F;
            }
            case LYING -> {
                // 仰面平躺：骨盆贴地，躯干水平仰卧，四肢自然伸展放松
                this.pelvis.y = PELVIS_DEFAULT_Y + 7.5F; // 贴紧地面
                this.pelvis.xRot = -1.57F; // 躯干水平仰卧

                this.head.xRot = 0.05F; // 头部平枕地面
                this.head.yRot = radHeadYaw * 0.4F;

                // 双臂自然放松贴在体侧
                this.rightArm.xRot = 0.0F;
                this.rightArm.zRot = 0.2F;
                this.rightForearm.xRot = -0.15F;

                this.leftArm.xRot = 0.0F;
                this.leftArm.zRot = -0.2F;
                this.leftForearm.xRot = -0.15F;

                // 双腿平伸微外展放松
                this.rightLeg.xRot = 0.0F;
                this.rightLeg.zRot = 0.1F;
                this.leftLeg.xRot = 0.0F;
                this.leftLeg.zRot = -0.1F;
            }
            case HOLDING_BELLY_GROUND -> {
                // 捂腹侧撑在地：半侧卧倒地，左肘撑地支起上半身，右手死死捂住腹部，双腿因剧痛蜷屈
                this.pelvis.y = PELVIS_DEFAULT_Y + 6.5F; // 骨盆贴近地面
                this.pelvis.xRot = -1.2F; // 上半身后仰约 70 度半撑起
                this.pelvis.zRot = 0.55F; // 身体向左侧侧倾
                this.chest.xRot = 0.3F; // 痛苦蜷腹含胸
                this.chest.yRot = -0.2F;

                this.head.xRot = 0.15F + radHeadPitch * 0.4F;
                this.head.yRot = -0.3F + radHeadYaw * 0.4F;

                // 左臂支撑地面：大臂后撑，小臂弯折按地
                this.leftArm.xRot = 0.4F;
                this.leftArm.yRot = -0.2F;
                this.leftArm.zRot = -0.75F;
                this.leftForearm.xRot = -1.4F;
                this.leftForearm.zRot = 0.3F;

                // 右手紧捂腹部：大臂内收，小臂横折紧贴腹部
                this.rightArm.xRot = -0.65F;
                this.rightArm.yRot = -0.5F;
                this.rightArm.zRot = 0.4F;
                this.rightForearm.xRot = -1.75F;
                this.rightForearm.yRot = -0.2F;

                // 双腿蜷曲：右腿在上深屈拱起，左腿在下贴地微弯
                this.rightLeg.xRot = -0.85F;
                this.rightLeg.yRot = -0.2F;
                this.rightLeg.zRot = 0.3F;
                this.rightShin.xRot = 1.3F; // 右膝拱起

                this.leftLeg.xRot = -0.25F;
                this.leftLeg.zRot = -0.2F;
                this.leftShin.xRot = 0.45F;
            }
            case HANDS_ON_HEAD -> {
                // 双手抱头：双臂上抬折向脑后/头顶护头，身躯含胸微蹲，表现惊恐或受降防卫
                this.pelvis.y = PELVIS_DEFAULT_Y + 1.2F; // 稍沉重心
                this.pelvis.xRot = 0.25F; // 躯干微躬防卫
                this.chest.xRot = 0.2F; // 含胸收拢

                this.head.xRot = 0.4F + radHeadPitch * 0.3F; // 头部低下埋在手臂间
                this.head.yRot = radHeadYaw * 0.3F;

                // 右臂高举折叠抱头
                this.rightArm.xRot = -2.6F;
                this.rightArm.yRot = -0.35F;
                this.rightArm.zRot = 0.4F;
                this.rightForearm.xRot = -1.9F; // 小臂反扣后脑
                this.rightForearm.yRot = -0.25F;

                // 左臂高举折叠抱头（对称护头）
                this.leftArm.xRot = -2.6F;
                this.leftArm.yRot = 0.35F;
                this.leftArm.zRot = -0.4F;
                this.leftForearm.xRot = -1.9F;
                this.leftForearm.yRot = 0.25F;

                // 双腿微屈开立，稳住防守重心
                this.rightLeg.xRot = -0.2F;
                this.rightLeg.zRot = 0.15F;
                this.rightShin.xRot = 0.35F;

                this.leftLeg.xRot = -0.2F;
                this.leftLeg.zRot = -0.15F;
                this.leftShin.xRot = 0.35F;
            }
            case REST -> {
                // 自然待机站立与呼吸律动，行走时伴随膝关节/肘关节的细微屈折
                this.head.xRot = radHeadPitch;
                this.head.yRot = radHeadYaw;

                if (limbSwingAmount > 0.01F) {
                    // 移动行走：手肘微屈，腿部迈步时膝盖有自然的弯曲过渡
                    float walkCycle = limbSwing * 0.6662F;
                    this.rightLeg.xRot =
                        Mth.cos(walkCycle) * 1.0F * limbSwingAmount;
                    this.rightShin.xRot = Math.max(
                        0.0F,
                        Mth.sin(walkCycle) * 0.8F * limbSwingAmount
                    );

                    this.leftLeg.xRot =
                        Mth.cos(walkCycle + (float) Math.PI) *
                        1.0F *
                        limbSwingAmount;
                    this.leftShin.xRot = Math.max(
                        0.0F,
                        Mth.sin(walkCycle + (float) Math.PI) *
                            0.8F *
                            limbSwingAmount
                    );

                    this.rightArm.xRot =
                        Mth.cos(walkCycle + (float) Math.PI) *
                        0.7F *
                        limbSwingAmount;
                    this.rightForearm.xRot = -0.25F; // 行走手肘微曲
                    this.leftArm.xRot =
                        Mth.cos(walkCycle) * 0.7F * limbSwingAmount;
                    this.leftForearm.xRot = -0.25F;
                } else {
                    // 待机状态：微重心单腿支撑（放松架势）
                    this.pelvis.zRot = 0.04F; // 重心微偏右脚
                    this.rightLeg.zRot = -0.04F;
                    this.leftLeg.zRot = 0.08F; // 左腿微外八放松
                    this.leftShin.xRot = 0.1F; // 左膝微屈

                    // 双臂自然垂于大腿两侧，小臂稍向内微合
                    this.rightArm.zRot = 0.08F;
                    this.rightForearm.xRot = -0.15F;
                    this.leftArm.zRot = -0.08F;
                    this.leftForearm.xRot = -0.15F;
                }
            }
        }
    }

    @Override
    public void renderToBuffer(
        PoseStack poseStack,
        VertexConsumer buffer,
        int packedLight,
        int packedOverlay,
        int color
    ) {
        this.root.render(poseStack, buffer, packedLight, packedOverlay, color);
    }
}
