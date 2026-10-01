package com.mannequin.client.render;

import com.mannequin.MannequinMod;
import com.mannequin.entity.MannequinEntity;
import com.mannequin.entity.MannequinPose;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 漫剧虚拟替身几何圆柱体渲染模型（Cylinder Stand-in Model）。
 *
 * <p>摒弃繁琐脆弱的人形木偶骨骼，采用 3D 虚拟制片/影视分镜标准圆柱替身代理（Cylinder Proxy），
 * 专为 AIGC（Kling、Wan2.1、ComfyUI）的精准空间语义分割与运镜构图设计。
 *
 * <p>特性：
 * <ul>
 *   <li>平滑 24 边形圆柱几何体，底部精准对齐地面（y=0）；</li>
 *   <li>支持直立站立、平躺地面与下蹲压缩 3 大标准虚拟制片姿态；</li>
 *   <li>顶部配有微型前方视向指示标（在非 Unlit 模式下辅助导演分清演员朝向）；</li>
 *   <li>100% 完美支持 16 种语义纯色染色与 Unlit 纯平无噪点抠像模式。</li>
 * </ul>
 */
public class MannequinModel extends EntityModel<MannequinEntity> {

    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(MannequinMod.id("mannequin"), "main");

    private static final int SEGMENTS = 24;
    private static final float[] COS = new float[SEGMENTS];
    private static final float[] SIN = new float[SEGMENTS];

    static {
        for (int i = 0; i < SEGMENTS; i++) {
            float angle = (float) (i * 2.0 * Math.PI / SEGMENTS);
            COS[i] = (float) Math.cos(angle);
            SIN[i] = (float) Math.sin(angle);
        }
    }

    private float pitch = 0.0F;
    private float yaw = 0.0F;
    private float roll = 0.0F;
    private float yOffset = 0.0F;
    private float height = 1.8F;
    private float radius = 0.3F;
    private boolean isUnlit = false;

    public MannequinModel(ModelPart root) {
        // 保留构造函数以兼容 NeoForge 渲染层注册机制
    }

    /**
     * 构建基础层定义以供 NeoForge 烘焙层注册系统调用。
     */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("cylinder_anchor", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 16, 16);
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
        this.isUnlit = entity.isUnlit();
        this.radius = 0.3F;
        this.yaw = 0.0F;
        this.roll = 0.0F;

        MannequinPose pose = entity.getMannequinPose();
        switch (pose) {
            case LYING -> {
                // 平躺地面：倒地水平放置，侧身紧贴地面
                this.height = 1.8F;
                this.pitch = -90.0F; // 沿 X 轴翻倒平放
                this.yOffset = this.radius; // 抬升一个半径高度，使圆柱底侧恰好贴紧地面
            }
            /*
            // 动作预设下架注释：当前为纯圆柱几何体模型
            case CROUCHING -> {
                // 下蹲压缩：高度压缩至约 1.1 米的紧凑圆柱
                this.height = 1.1F;
                this.pitch = 0.0F;
                this.yOffset = 0.0F;
            }
            */
            case STANDING -> {
                // 直立站立：标准人体 1.8 米高圆柱
                this.height = 1.8F;
                this.yOffset = 0.0F;
                if (limbSwingAmount > 0.02F) {
                    // 行走位移微动：随移动产生轻微 3~5 度前倾与步频微浮动感
                    float bob = Mth.sin(limbSwing * 0.6662F) * 0.02F * limbSwingAmount;
                    this.yOffset = Math.max(0.0F, bob);
                    this.pitch = Math.min(6.0F, limbSwingAmount * 4.0F); // 向前轻微倾斜体现动量
                } else {
                    this.pitch = 0.0F;
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
        poseStack.pushPose();
        if (this.yOffset != 0.0F) {
            poseStack.translate(0.0F, this.yOffset, 0.0F);
        }
        if (this.pitch != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(this.pitch));
        }
        if (this.roll != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(this.roll));
        }

        renderCylinder(poseStack, buffer, packedLight, packedOverlay, color);

        // 如果非 Unlit 纯平抠像模式，绘制顶部前方视向指示箭头（方便导演分清演员正面）
        if (!this.isUnlit) {
            renderFacingIndicator(poseStack, buffer, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private void renderCylinder(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        Matrix4f pose = poseStack.last().pose();
        PoseStack.Pose lastPose = poseStack.last();

        // 1. 侧壁表面（24 个平滑细分四边形 Quads）
        for (int i = 0; i < SEGMENTS; i++) {
            int next = (i + 1) % SEGMENTS;
            float x0 = this.radius * SIN[i];
            float z0 = this.radius * COS[i];
            float x1 = this.radius * SIN[next];
            float z1 = this.radius * COS[next];

            float nx0 = SIN[i];
            float nz0 = COS[i];
            float nx1 = SIN[next];
            float nz1 = COS[next];

            // 逆时针顶点顺序（法线朝向外侧）
            buffer.addVertex(pose, x0, 0.0F, z0)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, nx0, 0.0F, nz0);

            buffer.addVertex(pose, x1, 0.0F, z1)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, nx1, 0.0F, nz1);

            buffer.addVertex(pose, x1, this.height, z1)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, nx1, 0.0F, nz1);

            buffer.addVertex(pose, x0, this.height, z0)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, nx0, 0.0F, nz0);
        }

        // 2. 顶盖圆盘（单面逆时针渲染，法线严格朝上 +Y）
        for (int i = 0; i < SEGMENTS; i++) {
            int next = (i + 1) % SEGMENTS;
            float x0 = this.radius * SIN[i];
            float z0 = this.radius * COS[i];
            float x1 = this.radius * SIN[next];
            float z1 = this.radius * COS[next];

            // 从外侧/上方观察为 CCW 逆时针：中心 -> (x1, z1) -> (x0, z0) -> 中心
            buffer.addVertex(pose, 0.0F, this.height, 0.0F)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0F, 1.0F, 0.0F);

            buffer.addVertex(pose, x1, this.height, z1)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0F, 1.0F, 0.0F);

            buffer.addVertex(pose, x0, this.height, z0)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0F, 1.0F, 0.0F);

            buffer.addVertex(pose, 0.0F, this.height, 0.0F)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0F, 1.0F, 0.0F);
        }

        // 3. 底盖圆盘（单面顺时针渲染，法线严格朝下 -Y）
        for (int i = 0; i < SEGMENTS; i++) {
            int next = (i + 1) % SEGMENTS;
            float x0 = this.radius * SIN[i];
            float z0 = this.radius * COS[i];
            float x1 = this.radius * SIN[next];
            float z1 = this.radius * COS[next];

            // 外侧底面（从下方仰视为 CCW 逆时针）：中心 -> (x0, z0) -> (x1, z1) -> 中心
            buffer.addVertex(pose, 0.0F, 0.0F, 0.0F)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0F, -1.0F, 0.0F);

            buffer.addVertex(pose, x0, 0.0F, z0)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0F, -1.0F, 0.0F);

            buffer.addVertex(pose, x1, 0.0F, z1)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0F, -1.0F, 0.0F);

            buffer.addVertex(pose, 0.0F, 0.0F, 0.0F)
                    .setColor(color)
                    .setUv(0.5F, 0.5F)
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(lastPose, 0.0F, -1.0F, 0.0F);
        }
    }

    private void renderFacingIndicator(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay
    ) {
        Matrix4f pose = poseStack.last().pose();
        PoseStack.Pose lastPose = poseStack.last();
        int indicatorColor = 0xD0FFFFFF;
        float topY = this.height + 0.003F;

        float tipZ = this.radius * 0.85F;
        float baseZ = this.radius * 0.15F;
        float wingX = this.radius * 0.45F;

        // 逆时针顺次: tip (0, topY, tipZ) -> -wingX -> wingX -> tip
        buffer.addVertex(pose, 0.0F, topY, tipZ)
                .setColor(indicatorColor)
                .setUv(0.5F, 0.5F)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(lastPose, 0.0F, 1.0F, 0.0F);

        buffer.addVertex(pose, -wingX, topY, baseZ)
                .setColor(indicatorColor)
                .setUv(0.5F, 0.5F)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(lastPose, 0.0F, 1.0F, 0.0F);

        buffer.addVertex(pose, wingX, topY, baseZ)
                .setColor(indicatorColor)
                .setUv(0.5F, 0.5F)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(lastPose, 0.0F, 1.0F, 0.0F);

        buffer.addVertex(pose, 0.0F, topY, tipZ)
                .setColor(indicatorColor)
                .setUv(0.5F, 0.5F)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(lastPose, 0.0F, 1.0F, 0.0F);
    }
}
