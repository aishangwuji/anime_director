package com.mannequin.client.timeline;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 客户端人偶轨迹与附身状态清理门面（安全隔离类加载）。
 */
public final class ClientTrackCleanupHelper {

    private ClientTrackCleanupHelper() {
    }

    /**
     * 单个人偶实体销毁时同步清理其时间轴轨迹。
     *
     * @param uuidString 目标实体 UUID 字符串
     */
    public static void cleanupTrack(String uuidString) {
        if (uuidString == null) {
            return;
        }
        MasterClockEngine.INSTANCE.removeTrack(uuidString);

        if (PuppeteerController.INSTANCE.isPossessing()) {
            Entity possessed = PuppeteerController.INSTANCE.getPossessedEntity();
            if (possessed != null && uuidString.equals(possessed.getUUID().toString())) {
                PuppeteerController.INSTANCE.releasePossession();
            }
        }
    }

    /**
     * 范围清理：清理指定区域内的所有人偶轨迹，并自动排查孤立轨迹。
     *
     * @param centerPos 中心方块坐标
     * @param radius    清理球体/立方体半径
     */
    public static void cleanupRangeTracks(BlockPos centerPos, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || centerPos == null) {
            return;
        }

        AABB area = new AABB(centerPos).inflate(radius);
        List<Entity> list = mc.level.getEntities((Entity) null, area, entity -> entity instanceof com.mannequin.entity.MannequinEntity);
        for (Entity e : list) {
            cleanupTrack(e.getUUID().toString());
        }

        // 进一步清理可能已被服务端删除但在客户端世界仍存留轨迹的孤立轨道
        MasterClockEngine.INSTANCE.cleanupOrphanTracks();
    }
}
