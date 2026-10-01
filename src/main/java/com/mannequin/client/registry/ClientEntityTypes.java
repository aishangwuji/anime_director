package com.mannequin.client.registry;

import com.mannequin.MannequinMod;
import com.mannequin.client.camera.CameraAnchorEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 客户端专属实体类型注册器（Client-Only Entity Types）。
 *
 * <p>将客户端运镜相机锚点等专用实体类型完全隔离在客户端包与注册器中，
 * 彻底消除 Dedicated Server 类加载时解析客户端类产生的 NoClassDefFoundError。
 */
public final class ClientEntityTypes {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MannequinMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CameraAnchorEntity>> CAMERA_ANCHOR =
            ENTITY_TYPES.register("camera_anchor", () -> EntityType.Builder
                    .<CameraAnchorEntity>of(CameraAnchorEntity::new, MobCategory.MISC)
                    .sized(0.0F, 0.0F)
                    .eyeHeight(0.0F)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build("camera_anchor"));

    private ClientEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
