package com.mannequin.client.persistence;

import com.mannequin.client.camera.DirectorCameraController;
import com.mannequin.client.camera.MultiCameraManager;
import com.mannequin.client.timeline.MasterClockEngine;
import com.mannequin.client.timeline.TimelineTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 漫剧片场工程数据持久化管理器（Studio Persistence Manager）。
 *
 * <p>负责按存档（World-Persistent）保存与恢复完整的导演工作区：
 * <ul>
 *   <li><b>演员动捕轨道</b>：场上所有人偶实体的位移与朝向动画数据（{@link TimelineTrack}）；</li>
 *   <li><b>多机位分镜站台</b>：用户设立的所有固定拍摄机位、角度与 FOV（{@link MultiCameraManager}）；</li>
 *   <li><b>机械滑轨运镜机位</b>：自由飞控打下的 Catmull-Rom 样条关键点（{@link DirectorCameraController}）；</li>
 *   <li><b>时间轴总时长</b>：当前片场的拍演总时长配置。</li>
 * </ul>
 */
public final class StudioPersistenceManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudioPersistenceManager.class);
    public static final StudioPersistenceManager INSTANCE = new StudioPersistenceManager();

    private static final String FILE_NAME = "mannequin_studio.nbt";

    private StudioPersistenceManager() {
    }

    /**
     * 获取当前存档/服务器专属的片场数据存储路径。
     *
     * @return 存储文件的 Path，若当前未加载世界则返回 null
     */
    public Path getStudioSavePath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }

        IntegratedServer singleplayerServer = mc.getSingleplayerServer();
        if (singleplayerServer != null) {
            // 单人游戏：直接保存在当前存档根目录 saves/<world_name>/mannequin_studio.nbt
            return singleplayerServer.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
        }

        // 多人联机：按照服务器 IP 区分独立片场存储
        String serverId = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "lan_world";
        String safeName = serverId.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return FMLPaths.CONFIGDIR.get().resolve("mannequin/worlds/client_studio_" + safeName + ".nbt");
    }

    /**
     * 保存当前片场的所有工程数据。
     *
     * @param async 是否在后台异步线程池执行落盘（录制结束等交互时设为 true 消除掉帧；退出世界时设为 false 保证完整写盘）
     */
    public void saveStudioScene(boolean async) {
        Path targetPath = getStudioSavePath();
        if (targetPath == null) {
            return;
        }

        // 1. 同步收集主线程内存快照
        CompoundTag root = new CompoundTag();

        // 演员动捕轨道
        CompoundTag tracksTag = new CompoundTag();
        for (TimelineTrack track : MasterClockEngine.INSTANCE.getTracks().values()) {
            tracksTag.put(track.getTrackId(), track.toNbt());
        }
        root.put("Tracks", tracksTag);
        root.putInt("TotalDurationTicks", MasterClockEngine.INSTANCE.getTotalDurationTicks());

        // 多机位列表
        MultiCameraManager.INSTANCE.toNbt(root);

        // 滑轨运镜关键帧
        DirectorCameraController.INSTANCE.dollyKeyframesToNbt(root);

        Runnable writeTask = () -> {
            try {
                if (targetPath.getParent() != null) {
                    Files.createDirectories(targetPath.getParent());
                }
                NbtIo.writeCompressed(root, targetPath);
                LOGGER.info("[Mannequin Studio] 片场工程数据已保存至: {}", targetPath);
            } catch (IOException e) {
                LOGGER.error("[Mannequin Studio] 保存片场数据失败: {}", e.getMessage(), e);
                sendFeedbackMessage("§c[导演系统] 片场工程保存失败: " + e.getMessage());
            }
        };

        if (async) {
            CompletableFuture.runAsync(writeTask);
        } else {
            writeTask.run();
        }
    }

    /**
     * 从当前存档文件中加载并恢复片场所有工程数据。
     */
    public void loadStudioScene() {
        // 先清理内存状态
        MasterClockEngine.INSTANCE.clearAllTracks();
        MultiCameraManager.INSTANCE.clearStations();
        DirectorCameraController.INSTANCE.clearDollyKeyframes();

        Path targetPath = getStudioSavePath();
        if (targetPath == null || !Files.exists(targetPath)) {
            return;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(targetPath, NbtAccounter.unlimitedHeap());

            // 1. 恢复时间轴时长
            if (root.contains("TotalDurationTicks")) {
                MasterClockEngine.INSTANCE.setTotalDurationTicks(root.getInt("TotalDurationTicks"));
            }

            // 2. 恢复演员动捕轨道
            if (root.contains("Tracks")) {
                CompoundTag tracksTag = root.getCompound("Tracks");
                for (String trackId : tracksTag.getAllKeys()) {
                    MasterClockEngine.INSTANCE.addTrack(TimelineTrack.fromNbt(trackId, tracksTag.getCompound(trackId)));
                }
            }

            // 3. 恢复固定多机位
            MultiCameraManager.INSTANCE.fromNbt(root);

            // 4. 恢复滑轨关键帧
            DirectorCameraController.INSTANCE.loadDollyKeyframesFromNbt(root);

            // 5. 提示导演
            int trackCount = MasterClockEngine.INSTANCE.getTracks().size();
            int stationCount = MultiCameraManager.INSTANCE.getStations().size();
            int kfCount = DirectorCameraController.INSTANCE.getDollyKeyframes().size();

            LOGGER.info("[Mannequin Studio] 成功加载存档片场数据: 轨道数={}, 机位数={}, 滑轨点={}",
                    trackCount, stationCount, kfCount);

            if (trackCount > 0 || stationCount > 0 || kfCount > 0) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal(String.format(
                            "§a[导演系统] 已自动恢复本存档片场工程（%d 条演员动捕轨，%d 个分镜机位，%d 个滑轨关键点）",
                            trackCount, stationCount, kfCount
                    )), false);
                }
            }
        } catch (Exception e) {
            LOGGER.error("[Mannequin Studio] 加载片场数据失败: {}", e.getMessage(), e);
            sendFeedbackMessage("§c[导演系统] 加载本存档片场数据出错: " + e.getMessage());
        }
    }

    private void sendFeedbackMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.execute(() -> {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal(message), false);
                }
            });
        }
    }
}
