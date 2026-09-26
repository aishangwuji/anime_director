package com.mannequin.client.camera;

import net.minecraft.world.phys.Vec3;

/**
 * 导演相机机位关键帧。
 *
 * <p>用于记录滑轨轨迹中的某一个特定构图点。包含摄像机的空间三维坐标、视角俯仰、偏航、
 * 镜头横滚倾斜角（Dutch Angle / Roll）以及镜头焦距 FOV。
 *
 * @param position 摄像机世界坐标位置 (X, Y, Z)
 * @param pitch    摄像机俯仰视角（度数，-90° 至 90°）
 * @param yaw      摄像机偏航旋转角（度数）
 * @param roll     摄像机横滚旋转角（度数，向左为负，向右为正，用于动漫斜角构图）
 * @param fov      当前关键帧的垂直视场角 FOV（度数）
 */
public record CameraKeyframe(
        Vec3 position,
        float pitch,
        float yaw,
        float roll,
        float fov
) {

    /**
     * 校验关键帧参数合法性，防止出现 NaN 或无限值导致渲染崩溃。
     */
    public CameraKeyframe {
        if (position == null) {
            position = Vec3.ZERO;
        }
        pitch = Float.isFinite(pitch) ? pitch : 0.0F;
        yaw = Float.isFinite(yaw) ? yaw : 0.0F;
        roll = Float.isFinite(roll) ? roll : 0.0F;
        fov = Float.isFinite(fov) && fov > 0.0F ? fov : 70.0F;
    }

    /**
     * 在两个关键帧之间进行线性插值（Lerp）。
     *
     * @param from 起始关键帧
     * @param to   目标关键帧
     * @param t    插值进度（0.0 ~ 1.0）
     * @return 插值生成的过渡机位关键帧
     */
    public static CameraKeyframe lerp(CameraKeyframe from, CameraKeyframe to, double t) {
        double clampedT = Math.max(0.0, Math.min(1.0, t));

        // 空间位置线性插值
        Vec3 pos = from.position.lerp(to.position, clampedT);

        // 角度使用最短路径插值，防止跨越 360° 时乱转
        float pitch = (float) (from.pitch + (to.pitch - from.pitch) * clampedT);
        float yawDelta = (to.yaw - from.yaw) % 360.0F;
        if (yawDelta > 180.0F) {
            yawDelta -= 360.0F;
        } else if (yawDelta < -180.0F) {
            yawDelta += 360.0F;
        }
        float rawYaw = (float) (from.yaw + yawDelta * clampedT);
        // 全局角度规范化至 [-180°, 180°]，杜绝长时间连续插值累积导致的数值漂移
        float yaw = ((rawYaw + 180.0F) % 360.0F + 360.0F) % 360.0F - 180.0F;

        float roll = (float) (from.roll + (to.roll - from.roll) * clampedT);
        float fov = (float) (from.fov + (to.fov - from.fov) * clampedT);

        return new CameraKeyframe(pos, pitch, yaw, roll, fov);
    }

    /**
     * 将相机关键帧序列化为 NBT 复合标签。
     */
    public net.minecraft.nbt.CompoundTag toNbt() {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putDouble("x", position.x);
        tag.putDouble("y", position.y);
        tag.putDouble("z", position.z);
        tag.putFloat("pitch", pitch);
        tag.putFloat("yaw", yaw);
        tag.putFloat("roll", roll);
        tag.putFloat("fov", fov);
        return tag;
    }

    /**
     * 从 NBT 复合标签反序列化构建相机关键帧。
     */
    public static CameraKeyframe fromNbt(net.minecraft.nbt.CompoundTag tag) {
        Vec3 pos = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
        float pitch = tag.getFloat("pitch");
        float yaw = tag.getFloat("yaw");
        float roll = tag.getFloat("roll");
        float fov = tag.contains("fov") ? tag.getFloat("fov") : 70.0F;
        return new CameraKeyframe(pos, pitch, yaw, roll, fov);
    }
}
