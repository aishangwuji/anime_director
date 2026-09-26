package com.mannequin.item;

import com.mannequin.entity.MannequinEntity;
import com.mannequin.registry.ModEntityTypes;
import com.mannequin.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 漫剧导演工杖通用操作辅助类（DirectorWandHelper）。
 *
 * <p>集中管理工杖的放置、单体回收、范围批量清场逻辑，彻底消除道具类之间的重复冗余代码。
 */
public final class DirectorWandHelper {

    private DirectorWandHelper() {
    }

    /**
     * 以指定坐标为中心执行范围批量清场。
     */
    public static boolean performRangeCleanup(Level level, Player player, BlockPos centerPos, double radius) {
        if (level.isClientSide()) {
            ClientActionFacade.cleanupRangeTracks(centerPos, radius);
            return true;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        AABB area = new AABB(centerPos).inflate(radius);
        List<MannequinEntity> list = level.getEntitiesOfClass(MannequinEntity.class, area);
        int count = list.size();
        for (MannequinEntity mannequin : list) {
            serverLevel.sendParticles(ParticleTypes.POOF, mannequin.getX(), mannequin.getY() + 1.0, mannequin.getZ(), 8, 0.2, 0.5, 0.2, 0.05);
            if (player != null && !player.getAbilities().instabuild) {
                mannequin.spawnAtLocation(ModItems.MANNEQUIN_SPAWN.get());
            }
            mannequin.discard();
        }

        if (count > 0) {
            serverLevel.playSound(null, centerPos, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
            if (player != null) {
                player.displayClientMessage(Component.literal(String.format("§e[导演工杖] 范围清场完成！已批量回收周围 §6%d §e个人偶实体及轨迹", count)), true);
            }
        } else if (player != null) {
            player.displayClientMessage(Component.literal(String.format("§7[导演工杖] 周围 %.0f 格内未发现任何人偶实体", radius)), true);
        }
        return count > 0;
    }

    /**
     * 回收指定人偶实体，并在生存模式下掉落物品。
     */
    public static void recycleMannequin(MannequinEntity mannequin, Player player) {
        Level level = mannequin.level();
        if (!level.isClientSide()) {
            if (player != null && !player.getAbilities().instabuild) {
                mannequin.spawnAtLocation(ModItems.MANNEQUIN_SPAWN.get());
            }
            mannequin.discard();
            level.playSound(null, mannequin.getX(), mannequin.getY(), mannequin.getZ(), SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 1.0F, 1.2F);
            if (level instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.POOF, mannequin.getX(), mannequin.getY() + 1.0, mannequin.getZ(), 10, 0.2, 0.5, 0.2, 0.05);
            }
            if (player != null) {
                player.displayClientMessage(Component.literal("§e[导演工杖] 已回收该人偶并清理其运动轨迹！"), true);
            }
        } else {
            ClientActionFacade.cleanupTrack(mannequin.getUUID().toString());
        }
    }

    /**
     * 在指定方块坐标生成一个 100% 标准基准人偶。
     */
    public static boolean spawnMannequin(Level level, Player player, BlockPos spawnPos, float yaw) {
        if (level.isClientSide()) {
            return true;
        }

        EntityType<MannequinEntity> type = ModEntityTypes.MANNEQUIN.get();
        MannequinEntity mannequin = type.create(level);
        if (mannequin == null) {
            return false;
        }

        mannequin.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, yaw, 0.0F);
        level.addFreshEntity(mannequin);
        level.playSound(null, spawnPos, SoundEvents.ARMOR_STAND_PLACE, SoundSource.PLAYERS, 1.0F, 1.0F);
        if (player != null) {
            player.displayClientMessage(Component.literal("§a[导演工杖] 已放置标准人偶 (100%)！右键人偶可缩放体型，Shift+右键可回收。"), true);
        }
        return true;
    }

    private static final class ClientActionFacade {
        private static void cleanupRangeTracks(BlockPos pos, double radius) {
            com.mannequin.client.timeline.ClientTrackCleanupHelper.cleanupRangeTracks(pos, radius);
        }

        private static void cleanupTrack(String uuid) {
            com.mannequin.client.timeline.ClientTrackCleanupHelper.cleanupTrack(uuid);
        }
    }
}
