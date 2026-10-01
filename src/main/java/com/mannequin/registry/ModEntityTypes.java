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
                    .clientTrackingRange(16)
                    .build("mannequin"));

    private ModEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
