package com.mannequin.client.camera;

import net.minecraft.world.phys.Vec3;

/**
 * 帧率无关的指数衰减阻尼平滑计算器（Exponential Decay Damping）。
 *
 * <p>原版旁观者模式（Spectator）的镜头加速与停止极其生硬，手搓鼠标容易产生高频细微抖动。
 * 在 AIGC 视频控视频管线中，忽快忽慢或微颤的镜头会导致大模型背景扭曲或撕裂。
 *
 * <p>本类使用物理学上的指数衰减平滑方程：
 * <pre>
 *   val(t) = target + (val(t-1) - target) * exp(-lambda * dt)
 * </pre>
 * 其中：
 * <ul>
 *   <li>{@code target}：目标期望值（用户当前输入的位移或朝向）</li>
 *   <li>{@code lambda}：阻尼系数/响应刚度（值越大响应越灵敏，值越小镜头越厚重丝滑）</li>
 *   <li>{@code dt}：帧间隔时间（以秒为单位，与渲染刷新率自适应解耦）</li>
 * </ul>
 */
public final class DampedValue {

    private DampedValue() {
    }

    /**
     * 对标量数值进行指数衰减平滑插值。
     *
     * @param current 当前实际值
     * @param target  目标期望值
     * @param lambda  平滑阻尼强度（推荐范围：4.0 ~ 16.0）
     * @param dt      时间步长（秒）
     * @return 插值更新后的平滑值
     */
    public static double update(double current, double target, double lambda, double dt) {
        if (dt <= 0.0) {
            return current;
        }
        double factor = Math.exp(-lambda * dt);
        return target + (current - target) * factor;
    }

    /**
     * 对角度值（以度为单位）进行最短路径指数衰减平滑插值。
     * 自动处理 0° 与 360°（或 -180° 与 180°）环绕边界，防止摄像机在跨越分界线时反向自转 360 度。
     *
     * @param currentAngleDeg 当前实际角度（度）
     * @param targetAngleDeg  目标期望角度（度）
     * @param lambda          平滑阻尼强度
     * @param dt              时间步长（秒）
     * @return 插值更新后的平滑角度
     */
    public static float updateAngle(float currentAngleDeg, float targetAngleDeg, double lambda, double dt) {
        if (dt <= 0.0) {
            return currentAngleDeg;
        }
        // 计算从当前角指向目标角的最短角位移，并归一化至 [-180, 180]
        float delta = (targetAngleDeg - currentAngleDeg) % 360.0F;
        if (delta > 180.0F) {
            delta -= 360.0F;
        } else if (delta < -180.0F) {
            delta += 360.0F;
        }

        double factor = Math.exp(-lambda * dt);
        float newAngle = currentAngleDeg + delta * (float) (1.0 - factor);

        // 将最终结果规范化至 [-180, 180] 闭区间
        float wrapped = (newAngle + 180.0F) % 360.0F;
        if (wrapped < 0.0F) {
            wrapped += 360.0F;
        }
        return wrapped - 180.0F;
    }

    /**
     * 对三维空间坐标向量（{@link Vec3}）进行指数衰减平滑插值。
     *
     * @param current 当前摄像机坐标
     * @param target  目标期望坐标
     * @param lambda  平滑阻尼强度
     * @param dt      时间步长（秒）
     * @return 插值更新后的摄像机新坐标
     */
    public static Vec3 updateVec3(Vec3 current, Vec3 target, double lambda, double dt) {
        if (dt <= 0.0) {
            return current;
        }
        double newX = update(current.x, target.x, lambda, dt);
        double newY = update(current.y, target.y, lambda, dt);
        double newZ = update(current.z, target.z, lambda, dt);
        return new Vec3(newX, newY, newZ);
    }
}
