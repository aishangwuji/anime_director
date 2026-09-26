package com.mannequin.client.timeline;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 单条时间轴运动轨道（Timeline Track）。
 *
 * <p>类似音乐编曲软件（DAW）中的音轨，每一条 Track 独立绑定一个拍摄主体（人偶实体、车辆载具、穿越机等），
 * 并存储该主体在全局时间轴上每一帧的运动姿态数据。
 */
public class TimelineTrack {

    private final String trackId;
    private String trackName;
    private final NavigableMap<Integer, MotionFrame> frames = new TreeMap<>();
    private boolean locked = false;

    /**
     * @param trackId   轨道唯一标识（通常为实体 UUID 的字符串形式，或 "camera"）
     * @param trackName 轨道可读名称（例如 "纯红人偶 #01"、"主运镜摄像机"）
     */
    public TimelineTrack(String trackId, String trackName) {
        this.trackId = trackId;
        this.trackName = trackName;
    }

    public String getTrackId() {
        return trackId;
    }

    public String getTrackName() {
        return trackName;
    }

    public void setTrackName(String trackName) {
        this.trackName = trackName;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    /**
     * 向该轨道追加或更新某一 Tick 的运动帧数据。
     *
     * @param frame 运动帧
     */
    public void recordFrame(MotionFrame frame) {
        if (!locked) {
            frames.put(frame.tick(), frame);
        }
    }

    /**
     * 根据当前时间轴的逻辑 Tick 与渲染细分 partialTick，进行平滑插值采样。
     *
     * <p>时序对齐语义：partialTick 表示当前渲染帧在上一逻辑 tick 之后流逝的时间比例（0.0F ~ 1.0F）。
     * 插值在 [tick - 1, tick] 区间进行，与 Minecraft 原生渲染插值严格对齐。
     *
     * @param tick        当前逻辑 Tick
     * @param partialTick 渲染帧插值偏移（0.0F ~ 1.0F）
     * @return 采样计算后的平滑机位/动作帧；若轨道为空则返回 null
     */
    public MotionFrame sample(int tick, float partialTick) {
        if (frames.isEmpty()) {
            return null;
        }

        // 计算连续时间刻度（包含子帧微秒偏移）
        double t = tick + Math.max(0.0F, partialTick);

        // 边界保护：若采样时间早于首帧或晚于末帧，直接返回端点帧
        if (t <= frames.firstKey()) {
            return frames.firstEntry().getValue();
        }
        if (t >= frames.lastKey()) {
            return frames.lastEntry().getValue();
        }

        // 获取左右两个最邻近的时间轴关键帧
        Map.Entry<Integer, MotionFrame> floorEntry = frames.floorEntry((int) Math.floor(t));
        Map.Entry<Integer, MotionFrame> ceilingEntry = frames.ceilingEntry((int) Math.ceil(t));

        if (floorEntry == null && ceilingEntry == null) {
            return null;
        }
        if (floorEntry == null) {
            return ceilingEntry.getValue();
        }
        if (ceilingEntry == null) {
            return floorEntry.getValue();
        }

        MotionFrame from = floorEntry.getValue();
        MotionFrame to = ceilingEntry.getValue();

        int tickSpan = to.tick() - from.tick();
        if (tickSpan <= 0) {
            return from;
        }

        // 计算在两帧之间的归一化插值权重
        float normalizedT = (float) Math.max(0.0, Math.min(1.0, (t - from.tick()) / (double) tickSpan));
        return from.interpolate(to, normalizedT);
    }

    /**
     * @return 获取该轨道的首帧数据（即 t=0 时的初始静止状态）
     */
    public MotionFrame getInitialFrame() {
        if (frames.isEmpty()) {
            return null;
        }
        return frames.firstEntry().getValue();
    }

    /**
     * 清空本轨道所有已录制帧。
     */
    public void clear() {
        if (!locked) {
            frames.clear();
        }
    }

    /**
     * @return 当前已录制帧总数
     */
    public int getFrameCount() {
        return frames.size();
    }

    /**
     * @return 帧数据集合的只读视图
     */
    public NavigableMap<Integer, MotionFrame> getFrames() {
        return Collections.unmodifiableNavigableMap(frames);
    }

    /**
     * 序列化写入 NBT 复合标签。
     */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("TrackId", trackId);
        tag.putString("TrackName", trackName);
        tag.putBoolean("Locked", locked);
        ListTag list = new ListTag();
        for (MotionFrame f : frames.values()) {
            list.add(f.toNbt());
        }
        tag.put("Frames", list);
        return tag;
    }

    /**
     * 从 NBT 复合标签反序列化构建轨道。
     */
    public static TimelineTrack fromNbt(String id, CompoundTag tag) {
        String name = tag.getString("TrackName");
        TimelineTrack track = new TimelineTrack(id, name.isEmpty() ? id : name);
        track.setLocked(tag.getBoolean("Locked"));
        ListTag list = tag.getList("Frames", 10);
        for (int i = 0; i < list.size(); i++) {
            track.recordFrame(MotionFrame.fromNbt(list.getCompound(i)));
        }
        return track;
    }
}
