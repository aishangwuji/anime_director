package com.mannequin.registry;

import com.mannequin.MannequinMod;
import com.mannequin.entity.MannequinEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers the mod's entity types.
 */
public final class ModEntityTypes {

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, MannequinMod.MOD_ID);

    /**
     * The poseable director's mannequin. Sized ~1.8 blocks tall to match the
     * 28 model-pixel (seven head) body defined in {@code MannequinModel}.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<MannequinEntity>> MANNEQUIN =
            ENTITY_TYPES.register("mannequin", () -> EntityType.Builder
                    .<MannequinEntity>of(MannequinEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.65F)
                    .clientTrackingRange(10)
                    .build("mannequin"));

    /**
     * 导演自由相机与穿越机专用的客户端虚拟视口锚点实体类型。
     *
     * <p>仅限客户端运镜控制器挂载摄像机视口使用，严禁在服务端召唤或存储。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.mannequin.client.camera.CameraAnchorEntity>> CAMERA_ANCHOR =
            ENTITY_TYPES.register("camera_anchor", () -> EntityType.Builder
                    .<com.mannequin.client.camera.CameraAnchorEntity>of(com.mannequin.client.camera.CameraAnchorEntity::new, MobCategory.MISC)
                    .sized(0.0F, 0.0F)
                    .eyeHeight(0.0F)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .build("camera_anchor"));

    private ModEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
