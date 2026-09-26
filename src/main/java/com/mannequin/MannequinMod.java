package com.mannequin;

import com.mannequin.registry.ModEntityTypes;
import com.mannequin.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * Common entry point for the Mannequin Director mod.
 *
 * <p>Owns the shared identity of the mod and wires up the common-side
 * {@code DeferredRegister}s. Client-only behaviour lives in
 * {@code com.mannequin.client}.
 */
@Mod(MannequinMod.MOD_ID)
public final class MannequinMod {

    public static final String MOD_ID = "mannequin";

    public MannequinMod(IEventBus modEventBus, ModContainer modContainer) {
        ModEntityTypes.register(modEventBus);
        ModItems.register(modEventBus);
        modEventBus.addListener(this::registerPayloadHandlers);

        // 若处于物理客户端环境，初始化客户端专属事件与渲染器
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.mannequin.client.MannequinClient.init(modEventBus);
        }
    }

    /**
     * 注册双端自定义网络载荷处理器（用于联机动捕位移同步）。
     */
    private void registerPayloadHandlers(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event) {
        event.registrar(MOD_ID).playToServer(
                com.mannequin.network.SyncMannequinPosPayload.TYPE,
                com.mannequin.network.SyncMannequinPosPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        net.minecraft.world.entity.Entity entity = context.player().level().getEntity(payload.entityId());
                        if (entity != null) {
                            entity.setPos(payload.x(), payload.y(), payload.z());
                            entity.setYRot(payload.yaw());
                            entity.setXRot(payload.pitch());
                            entity.setDeltaMovement(payload.vx(), payload.vy(), payload.vz());
                        }
                    });
                }
        );
    }

    /**
     * Creates a {@link ResourceLocation} in this mod's namespace.
     *
     * @param path the path segment, without a leading slash
     * @return the namespaced identifier, e.g. {@code mannequin:textures/entity/mannequin.png}
     */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
