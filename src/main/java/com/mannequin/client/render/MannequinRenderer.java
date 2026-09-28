package com.mannequin.client.render;

import com.mannequin.MannequinMod;
import com.mannequin.entity.MannequinEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a mannequin as a flat, textureless solid color.
 *
 * <p>Supports two looks: a shaded color that respects scene lighting (good for
 * depth/lineart conditioning), and an unlit flat color that ignores lighting
 * entirely (good for clean segmentation masks).
 */
public class MannequinRenderer extends EntityRenderer<MannequinEntity> {

    private static final ResourceLocation TEXTURE = MannequinMod.id("textures/entity/mannequin.png");

    private final MannequinModel model;

    public MannequinRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new MannequinModel(context.bakeLayer(MannequinModel.LAYER_LOCATION));
    }

    @Override
    public void render(MannequinEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        float scale = entity.getScale();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));
        float limbSwing = entity.getLimbSwing(partialTick);
        float limbSwingAmount = entity.getLimbSwingAmount(partialTick);
        this.model.setupAnim(entity, limbSwing, limbSwingAmount, entity.tickCount + partialTick, 0.0F, entity.getXRot());

        int effectiveLight = entity.isUnlit() ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : packedLight;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));
        this.model.renderToBuffer(poseStack, buffer, effectiveLight, OverlayTexture.NO_OVERLAY,
                entity.getColor().argb());
        poseStack.popPose();

        this.shadowRadius = entity.isUnlit() ? 0.0F : (0.35F * scale);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(MannequinEntity entity) {
        return TEXTURE;
    }
}
