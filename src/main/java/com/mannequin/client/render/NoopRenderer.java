package com.mannequin.client.render;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * 空渲染器（No-op Renderer）。
 *
 * <p>专用于相机锚点等纯逻辑/隐形实体，跳过所有模型构建与光栅化渲染，保证零 GPU 开销。
 *
 * @param <T> 实体泛型
 */
public class NoopRenderer<T extends Entity> extends EntityRenderer<T> {

    public NoopRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(T livingEntity, Frustum camera, double camX, double camY, double camZ) {
        return false;
    }

    private static final ResourceLocation DUMMY =
            com.mannequin.MannequinMod.id("textures/entity/camera_anchor.png");

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return DUMMY;
    }
}
