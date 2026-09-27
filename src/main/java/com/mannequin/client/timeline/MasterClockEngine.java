package com.mannequin.client.timeline;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全局主时钟与虚拟制片调度引擎（Master Clock Engine）。
 *
 * <p>本类掌控整个漫剧片场的时间维度与多轨生命周期调度：
 * <ul>
 *   <li><b>多轨叠录（Multi-Track Overdubbing）</b>：类似 DAW 录音棚，支持一个一个角色轮流动捕，录制时其他角色全自动“幽灵伴跑”；</li>
 *   <li><b>一键倒带复位（Rewind to 0s）</b>：拍摄完毕或中断后，场上所有人偶与道具瞬间归位到第 0 秒初始坐标，准备下一条排演；</li>
 *   <li><b>时间轴洗带（Scrubbing）</b>：支持快速跳转至第几秒，场上所有人偶同步定格在该时间节点。</li>
 * </ul>
 */
public final class MasterClockEngine {

    public static final MasterClockEngine INSTANCE = new MasterClockEngine();

    /** 专用于摄像机运镜的轨道保留标识符 */
    public static final String CAMERA_TRACK_ID = "track_camera";

    /**
     * 主时钟运行状态枚举。
     */
    public enum State {
        /** 静止定格状态 */
        STOPPED,
        /** 多轨整体排演播放中 */
        PLAYING,
        /** 某单轨动捕录制中（其他轨道幽灵伴跑） */
        RECORDING
    }

    /**
     * 全局演播时间流速（Time Scale / Bullet Time）。
     */
    public enum TimeScale {
        BULLET_TIME(0.25, "0.25x (子弹时间)"),
        SLOW_MOTION(0.50, "0.5x (慢动作)"),
        NORMAL(1.00, "1.0x (原速)"),
        FAST(2.00, "2.0x (快进)");

        private final double scale;
        private final String displayName;

        TimeScale(double scale, String displayName) {
            this.scale = scale;
            this.displayName = displayName;
        }

        public double getScale() {
            return scale;
        }

        public String getDisplayName() {
            return displayName;
        }

        public TimeScale next() {
            TimeScale[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private static final int[] DURATION_PRESETS = {120, 200, 300, 600, 1200}; // 6s, 10s, 15s, 30s, 60s

    private State state = State.STOPPED;
    private TimeScale timeScale = TimeScale.NORMAL;
    private double customTimeScale = 1.0; // 连续无级演播速率 (0.05x ~ 3.00x)
    private int totalDurationTicks = 120; // 默认 120 Ticks = 6 秒 (基于 20 TPS)
    private int currentTick = 0;
    private double playbackTime = 0.0;

    private final Map<String, TimelineTrack> tracks = new LinkedHashMap<>();
    private String activeRecordingTrackId = null;

    private MasterClockEngine() {
    }

    public State getState() {
        return state;
    }

    public TimeScale getTimeScale() {
        return timeScale;
    }

    public double getTimeScaleValue() {
        return customTimeScale;
    }

    public void setTimeScaleValue(double scale) {
        this.customTimeScale = Math.max(0.05, Math.min(3.0, scale));
    }

    public void setTimeScale(TimeScale timeScale) {
        this.timeScale = (timeScale != null) ? timeScale : TimeScale.NORMAL;
        this.customTimeScale = this.timeScale.getScale();
    }

    public void cycleTimeScale() {
        this.timeScale = this.timeScale.next();
        this.customTimeScale = this.timeScale.getScale();
    }

    public void cycleDuration() {
        int nextDuration = DURATION_PRESETS[0];
        for (int d : DURATION_PRESETS) {
            if (d > totalDurationTicks) {
                nextDuration = d;
                break;
            }
        }
        setTotalDurationTicks(nextDuration);
        saveToDisk();
        com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(true);
    }

    public int getTotalDurationTicks() {
        return totalDurationTicks;
    }

    public void setTotalDurationTicks(int totalDurationTicks) {
        this.totalDurationTicks = Math.max(40, Math.min(24000, totalDurationTicks));
    }

    public void setTotalDurationSeconds(double seconds) {
        setTotalDurationTicks((int) Math.round(seconds * 20.0));
    }

    public int getCurrentTick() {
        return currentTick;
    }

    public double getPlaybackTime() {
        return playbackTime;
    }

    /**
     * 获取考虑当前时间流速与渲染 partialTick 的精确连续时间刻度。
     *
     * @param partialTick 渲染帧插值系数
     * @return 连续浮点时间刻度（Tick 单位）
     */
    public double getSmoothPlaybackTime(float partialTick) {
        if (state == State.PLAYING) {
            return playbackTime + (double) partialTick * customTimeScale;
        }
        return (double) currentTick + (double) partialTick;
    }

    /**
     * @return 当前时间轴所处的秒数（保留两位小数）
     */
    public double getCurrentTimeSeconds() {
        if (state == State.PLAYING) {
            return playbackTime / 20.0;
        }
        return (double) currentTick / 20.0;
    }

    /**
     * @return 场景设定的总时长秒数
     */
    public double getTotalDurationSeconds() {
        return (double) totalDurationTicks / 20.0;
    }

    public String getActiveRecordingTrackId() {
        return activeRecordingTrackId;
    }

    public Map<String, TimelineTrack> getTracks() {
        return Collections.unmodifiableMap(tracks);
    }

    /**
     * 获取或创建指定主体的轨道。
     *
     * @param trackId   轨道唯一标识
     * @param trackName 轨道可读显示名称
     * @return 对应的轨道对象
     */
    public TimelineTrack getOrCreateTrack(String trackId, String trackName) {
        return tracks.computeIfAbsent(trackId, id -> new TimelineTrack(id, trackName));
    }

    /**
     * 注册或更新一条轨道到调度引擎。
     *
     * @param track 轨道对象
     */
    public void addTrack(TimelineTrack track) {
        tracks.put(track.getTrackId(), track);
    }

    /**
     * 启动多轨回放模式（实拍预览或伴跑排演）。
     */
    public void play() {
        if (state == State.STOPPED) {
            // 若当前内存中无轨道，自动尝试从本地磁盘恢复上一场排演数据
            if (tracks.isEmpty()) {
                loadFromDisk();
            }
            state = State.PLAYING;
        }
    }

    /**
     * 暂停时钟播放。
     */
    public void pause() {
        if (state != State.STOPPED) {
            state = State.STOPPED;
        }
    }

    /**
     * 启动对特定轨道的录制（动捕）。
     *
     * @param trackId 要录制的轨道 ID
     */
    public void startRecording(String trackId) {
        this.activeRecordingTrackId = trackId;
        this.currentTick = 0;
        this.playbackTime = 0.0;
        TimelineTrack track = tracks.get(trackId);
        if (track != null) {
            track.clear();
        }
        this.state = State.RECORDING;
    }

    /**
     * 停止录制并自动倒带复位与落盘归档。
     */
    public void stopRecording() {
        if (state == State.RECORDING) {
            state = State.STOPPED;
            activeRecordingTrackId = null;

            // 自动将总时长自适应扩展至本次录制的最大帧长度（向上对齐到整秒，最低 60 ticks = 3 秒）
            int maxTrackTick = 0;
            for (TimelineTrack t : tracks.values()) {
                if (!t.getFrames().isEmpty()) {
                    maxTrackTick = Math.max(maxTrackTick, t.getFrames().lastKey());
                }
            }
            if (maxTrackTick > 0) {
                int fitted = ((maxTrackTick + 19) / 20) * 20;
                this.totalDurationTicks = Math.max(60, fitted);
            }

            saveToDisk();
            com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(true);
            rewindToStart();
        }
    }

    /**
     * 安全移除指定轨道（当人偶被回收、清理或销毁时联动清理）。
     *
     * @param trackId 轨道标识符（对应人偶 UUID 字符串）
     * @return 若成功找到并移除了该轨道返回 true
     */
    public boolean removeTrack(String trackId) {
        if (trackId == null) {
            return false;
        }
        if (trackId.equals(activeRecordingTrackId)) {
            stopRecording();
        }
        TimelineTrack removed = tracks.remove(trackId);
        if (removed != null) {
            try {
                saveToDisk();
                com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(true);
            } catch (Throwable ignored) {
            }
            return true;
        }
        return false;
    }

    /**
     * 智能扫描并清理世界中不存在的孤立人偶轨道（Orphan Tracks）。
     */
    public int cleanupOrphanTracks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return 0;
        }
        java.util.Set<String> activeUuids = new java.util.HashSet<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            activeUuids.add(entity.getUUID().toString());
        }

        int removedCount = 0;
        java.util.Iterator<Map.Entry<String, TimelineTrack>> iterator = tracks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, TimelineTrack> entry = iterator.next();
            String id = entry.getKey();
            if (CAMERA_TRACK_ID.equals(id)) {
                continue;
            }
            if (!activeUuids.contains(id)) {
                iterator.remove();
                removedCount++;
            }
        }

        if (removedCount > 0) {
            saveToDisk();
            com.mannequin.client.persistence.StudioPersistenceManager.INSTANCE.saveStudioScene(true);
        }
        return removedCount;
    }

    /**
     * 清空全场所有轨道数据。
     */
    public void clearAllTracks() {
        tracks.clear();
        currentTick = 0;
        state = State.STOPPED;
    }

    /**
     * 将全场时间轴数据持久化保存到本地磁盘。
     * <p>在主线程同步抓取数据内存快照，并将耗时的高强度 GZIP 压缩与文件落盘卸载至异步线程池，杜绝掉帧卡顿。
     */
    public void saveToDisk() {
        CompoundTag root = new CompoundTag();
        CompoundTag tracksTag = new CompoundTag();
        for (TimelineTrack track : tracks.values()) {
            tracksTag.put(track.getTrackId(), track.toNbt());
        }
        root.put("Tracks", tracksTag);
        root.putInt("TotalDurationTicks", totalDurationTicks);
        root.putDouble("CustomTimeScale", customTimeScale);

        // 异步后台落盘，避免录制停止时造成主线程帧率尖峰卡顿
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                if (net.neoforged.fml.loading.FMLPaths.CONFIGDIR != null && net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get() != null) {
                    java.nio.file.Path dir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(com.mannequin.MannequinMod.MOD_ID);
                    java.nio.file.Files.createDirectories(dir);
                    net.minecraft.nbt.NbtIo.writeCompressed(root, dir.resolve("last_scene.nbt"));
                }
            } catch (Throwable e) {
                sendFeedbackMessage("§c[导演系统] 场景快照异步保存失败: " + e.getMessage());
            }
        });
    }



    /**
     * 发送客户端提示消息（线程安全与空指针安全保护）。
     */
    private void sendFeedbackMessage(String message) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), false);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 从本地磁盘恢复上一次排演的完整场景数据。
     */
    public void loadFromDisk() {
        try {
            java.nio.file.Path file = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("mannequin/last_scene.nbt");
            if (!java.nio.file.Files.exists(file)) {
                return;
            }
            CompoundTag root = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            int loaded = root.contains("TotalDurationTicks") ? root.getInt("TotalDurationTicks") : 120;
            this.totalDurationTicks = Math.max(40, loaded);
            if (root.contains("CustomTimeScale")) {
                this.customTimeScale = Math.max(0.05, Math.min(3.0, root.getDouble("CustomTimeScale")));
            }
            CompoundTag tracksTag = root.getCompound("Tracks");
            tracks.clear();
            for (String id : tracksTag.getAllKeys()) {
                tracks.put(id, TimelineTrack.fromNbt(id, tracksTag.getCompound(id)));
            }

            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && !tracks.isEmpty()) {
                mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("§a[导演系统] 已自动恢复上一次排演场景（共 " + tracks.size() + " 条动捕轨道）"), true);
            }
        } catch (java.io.IOException ignored) {
        }
    }

    /**
     * 一键倒带复位回第 0 秒（初始机位与初始演员站桩点）。
     */
    public void rewindToStart() {
        currentTick = 0;
        playbackTime = 0.0;
        applyTrackStatesToWorld(0, 0.0F);
    }

    /**
     * 时间轴洗带（Scrubbing）：将片场各实体状态强制对齐到指定 Tick。
     *
     * @param targetTick 目标时间点
     */
    public void scrubTo(int targetTick) {
        this.currentTick = Math.max(0, Math.min(totalDurationTicks, targetTick));
        this.playbackTime = (double) this.currentTick;
        applyTrackStatesToWorld(this.currentTick, 0.0F);
    }

    /**
     * 逻辑 Tick 推进（挂载在客户端 ClientTickEvent.Post 事件）。
     */
    public void onClientTick() {
        if (state == State.PLAYING) {
            playbackTime += customTimeScale;
            currentTick = (int) playbackTime;
            if (playbackTime >= totalDurationTicks) {
                // 播放结束，自动倒带回起点并停止
                state = State.STOPPED;
                rewindToStart();
                return;
            }
            float subTick = (float) (playbackTime - currentTick);
            applyTrackStatesToWorld(currentTick, subTick);
        } else if (state == State.RECORDING) {
            // 动捕录制当前帧并让其他轨道幽灵伴跑
            currentTick++;
            playbackTime = (double) currentTick;
            applyGhostPlayback(currentTick, 0.0F);

            if (currentTick >= totalDurationTicks) {
                // 达到指定最大时长，自动收杆
                stopRecording();
            }
        }
    }

    /**
     * 渲染帧平滑插值推进（消除 20 TPS 离散跳跃，提供 144Hz 丝滑体验）。
     *
     * @param partialTick 渲染子帧时间差（0.0F ~ 1.0F）
     */
    public void onRenderTick(float partialTick) {
        if (state == State.PLAYING) {
            double smoothTime = playbackTime + (double) partialTick * customTimeScale;
            int tick = (int) smoothTime;
            float subTick = (float) (smoothTime - tick);
            applyTrackStatesToWorld(tick, subTick);
        } else if (state == State.RECORDING) {
            applyGhostPlayback(currentTick, partialTick);
        }
    }

    /**
     * 将全场所有轨道的数据更新映射到游戏世界中的对应实体。
     */
    private void applyTrackStatesToWorld(int tick, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        for (TimelineTrack track : tracks.values()) {
            applyTrackToEntity(track, tick, partialTick, level);
        }
    }

    /**
     * 幽灵伴跑：仅更新非录制中的轨道，使已有演员与车辆在旁边自动动起来。
     */
    private void applyGhostPlayback(int tick, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        for (TimelineTrack track : tracks.values()) {
            if (!track.getTrackId().equals(activeRecordingTrackId)) {
                applyTrackToEntity(track, tick, partialTick, level);
            }
        }
    }

    /**
     * 将单条轨道在特定时间的插值状态下发给实体对象。
     */
    private void applyTrackToEntity(TimelineTrack track, int tick, float partialTick, ClientLevel level) {
        if (track.getTrackId().equals(CAMERA_TRACK_ID)) {
            // 摄像机轨道由专用的相机控制器接管渲染，不操作世界实体
            return;
        }

        MotionFrame frame = track.sample(tick, partialTick);
        if (frame == null) {
            return;
        }

        try {
            UUID entityUuid = UUID.fromString(track.getTrackId());
            Entity entity = null;
            for (Entity e : level.entitiesForRendering()) {
                if (e.getUUID().equals(entityUuid)) {
                    entity = e;
                    break;
                }
            }

            if (entity != null) {
                // 覆写实体空间坐标与朝向
                entity.setPos(frame.position().x, frame.position().y, frame.position().z);
                entity.setYRot(frame.yaw());
                entity.setXRot(frame.pitch());
                entity.yRotO = frame.yaw();
                entity.xRotO = frame.pitch();
            }
        } catch (IllegalArgumentException ignored) {
            // 非 UUID 标识的实体轨道忽略
        }
    }
}
