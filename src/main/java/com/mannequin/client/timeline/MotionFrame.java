package com.mannequin.client.timeline;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/**
 * 运动轨迹数据采样帧（Motion Frame）。
 *
 * <p>用于记录某一时刻实体（人偶、载具、道具）或虚拟摄像机的空间刚体运动变换信息。
 * 采样基于 Minecraft 逻辑 Tick（默认 20 TPS），以实现极低内存开销与跨硬件确定性。
 *
 * @param tick           处于全局时间轴上的逻辑帧序号（0 ~ 总时长 Ticks）
 * @param position       空间世界绝对坐标 (X, Y, Z)
 * @param pitch          俯仰角（Pitch，单位：度，-90° 至 90°）
 * @param yaw            水平偏航角（Yaw，单位：度）
 * @param roll           横滚翻转角（Roll / Dutch Angle，单位：度，摄像机与航模常用）
 * @param fov            垂直视场角（FOV，度数，摄像机专用）
 * @param isActionActive 特殊动作触发标志（如是否正在奔跑冲刺、刹车、挥动等）
 */
public record MotionFrame(
        int tick,
        Vec3 position,
        float pitch,
        float yaw,
        float roll,
        float fov,
        boolean isActionActive
) {

    public MotionFrame {
        if (position == null) {
            position = Vec3.ZERO;
        }
        pitch = Float.isFinite(pitch) ? pitch : 0.0F;
        yaw = Float.isFinite(yaw) ? yaw : 0.0F;
        roll = Float.isFinite(roll) ? roll : 0.0F;
        fov = Float.isFinite(fov) && fov > 0.0F ? fov : 70.0F;
    }

    /**
     * 在前后两个采样帧之间进行高精度的子帧插值（Sub-tick Interpolation）。
     *
     * <p>在客户端渲染帧（如 60FPS、144FPS 甚至更高）中，通过 partialTick 在两帧间插值，
     * 保证回放与录制时具有工业级丝滑度，消除 20 TPS 的步进阶梯感。
     *
     * @param next        下一逻辑帧数据
     * @param partialTick 两帧之间的时间比例（0.0F ~ 1.0F）
     * @return 插值生成的平滑帧数据
     */
    public MotionFrame interpolate(MotionFrame next, float partialTick) {
        float t = Math.max(0.0F, Math.min(1.0F, partialTick));

        // 空间位置三维线性插值
        Vec3 pos = this.position.lerp(next.position, t);

        // 角度使用最短圆弧插值，避免跨越 360°/180° 分界线时自转
        float newPitch = this.pitch + (next.pitch - this.pitch) * t;

        float yawDelta = (next.yaw - this.yaw) % 360.0F;
        if (yawDelta > 180.0F) {
            yawDelta -= 360.0F;
        } else if (yawDelta < -180.0F) {
            yawDelta += 360.0F;
        }
        float newYaw = this.yaw + yawDelta * t;

        float newRoll = this.roll + (next.roll - this.roll) * t;
        float newFov = this.fov + (next.fov - this.fov) * t;

        return new MotionFrame(
                this.tick,
                pos,
                newPitch,
                newYaw,
                newRoll,
                newFov,
                t < 0.5F ? this.isActionActive : next.isActionActive
        );
    }

    /**
     * 序列化写入 NBT 复合标签，用于持久化归档。
     */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Tick", tick);
        tag.putDouble("X", position.x);
        tag.putDouble("Y", position.y);
        tag.putDouble("Z", position.z);
        tag.putFloat("Pitch", pitch);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Roll", roll);
        tag.putFloat("Fov", fov);
        tag.putBoolean("Action", isActionActive);
        return tag;
    }

    /**
     * 从 NBT 复合标签反序列化构建运动帧对象。
     */
    public static MotionFrame fromNbt(CompoundTag tag) {
        int tick = tag.getInt("Tick");
        Vec3 pos = new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"));
        float pitch = tag.getFloat("Pitch");
        float yaw = tag.getFloat("Yaw");
        float roll = tag.getFloat("Roll");
        float fov = tag.contains("Fov") ? tag.getFloat("Fov") : 70.0F;
        boolean action = tag.getBoolean("Action");
        return new MotionFrame(tick, pos, pitch, yaw, roll, fov, action);
    }
}
