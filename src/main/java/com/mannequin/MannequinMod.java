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
        ).playToServer(
                com.mannequin.network.SyncMannequinScalePayload.TYPE,
                com.mannequin.network.SyncMannequinScalePayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        net.minecraft.world.entity.Entity entity = context.player().level().getEntity(payload.entityId());
                        if (entity instanceof com.mannequin.entity.MannequinEntity mannequin) {
                            mannequin.setScale(payload.scale());
                        }
                    });
                }
        ).playToServer(
                com.mannequin.network.ExportBuildingPayload.TYPE,
                com.mannequin.network.ExportBuildingPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        net.minecraft.world.entity.player.Player player = context.player();
                        if (player != null && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                            net.minecraft.nbt.CompoundTag tag = com.mannequin.building.BuildingBlueprintHelper.exportBuilding(
                                    serverLevel, payload.posA(), payload.posB(), payload.name(), payload.author(), payload.description()
                            );
                            boolean ok = com.mannequin.building.BuildingBlueprintHelper.saveBlueprintFile(payload.fileName(), tag);
                            if (ok) {
                                player.displayClientMessage(
                                        net.minecraft.network.chat.Component.literal("§a[建筑蓝图] 建筑「" + payload.name() + "」已成功打包导出至 config/mannequin/blueprints/" + payload.fileName() + "!"),
                                        false
                                );
                            } else {
                                player.displayClientMessage(
                                        net.minecraft.network.chat.Component.literal("§c[建筑蓝图] 保存文件失败，请检查文件名与写入权限！"),
                                        false
                                );
                            }
                        }
                    });
                }
        ).playToServer(
                com.mannequin.network.PlaceBuildingPayload.TYPE,
                com.mannequin.network.PlaceBuildingPayload.STREAM_CODEC,
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        net.minecraft.world.entity.player.Player player = context.player();
                        if (player != null && player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                            boolean ok = com.mannequin.building.BuildingBlueprintHelper.placeBuilding(
                                    serverLevel, payload.targetOrigin(), payload.fileName(), payload.rotationDegrees()
                            );
                            if (ok) {
                                serverLevel.playSound(null, payload.targetOrigin(), net.minecraft.sounds.SoundEvents.ANVIL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.1F);
                                player.displayClientMessage(
                                        net.minecraft.network.chat.Component.literal("§a[建筑蓝图] 建筑已成功一键部署生成于片场！"),
                                        true
                                );
                            } else {
                                player.displayClientMessage(
                                        net.minecraft.network.chat.Component.literal("§c[建筑蓝图] 部署失败：未找到蓝图文件 " + payload.fileName() + " 或数据损坏！"),
                                        true
                                );
                            }
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
