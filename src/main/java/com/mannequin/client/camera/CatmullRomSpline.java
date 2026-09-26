package com.mannequin.client.camera;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 基于 Catmull-Rom 样条曲线的电影机械滑轨插值器。
 *
 * <p>特性优势：
 * <ul>
 *   <li><b>严格穿点</b>：生成的平滑曲线必然经过导演打下的每一个关键机位（Keyframe），所见即所得。</li>
 *   <li><b>切线连续性（C1 连续）</b>：转弯与起伏处弧度天然平滑，绝无折角或机械顿挫。</li>
 *   <li><b>弧长匀速参数化（Arc-Length Parameterization）</b>：
 *       传统参数曲线按点间隔步进会导致“在点密集处慢、点稀疏处飞快”。本类通过高精度弦长积分重采样，
 *       确保相机在整段运动过程中始终保持<b>绝对匀速</b>，解决视频大模型对加速度微变导致的画面撕裂问题。</li>
 * </ul>
 */
public final class CatmullRomSpline {

    private final List<CameraKeyframe> keyframes;
    private final List<Double> cumulativeLengths;
    private final double totalLength;

    /**
     * 构建一条机械滑轨样条曲线。
     *
     * @param keyframes 关键帧列表，至少包含 2 个机位
     */
    public CatmullRomSpline(List<CameraKeyframe> keyframes) {
        if (keyframes == null || keyframes.size() < 2) {
            throw new IllegalArgumentException("构建样条滑轨至少需要 2 个关键机位！");
        }
        this.keyframes = List.copyOf(keyframes);

        // 预采样弧长表，用于匀速步进重映射
        int segments = this.keyframes.size() - 1;
        int samplesPerSegment = 20;
        int totalSamples = segments * samplesPerSegment;

        List<Double> lengths = new ArrayList<>(totalSamples + 1);
        lengths.add(0.0);

        double accum = 0.0;
        Vec3 prevPos = evaluateRawPosition(0.0);

        for (int i = 1; i <= totalSamples; i++) {
            double rawT = (double) i / (double) totalSamples;
            Vec3 currPos = evaluateRawPosition(rawT);
            accum += prevPos.distanceTo(currPos);
            lengths.add(accum);
            prevPos = currPos;
        }

        this.cumulativeLengths = Collections.unmodifiableList(lengths);
        this.totalLength = Math.max(1e-5, accum);
    }

    /**
     * @return 滑轨总空间物理长度（方块/米）
     */
    public double getTotalLength() {
        return totalLength;
    }

    /**
     * @return 关键帧列表副本
     */
    public List<CameraKeyframe> getKeyframes() {
        return keyframes;
    }

    /**
     * 匀速采样：输入归一化时间进度 u（0.0 ~ 1.0），返回匀速插值后的机位状态。
     *
     * @param u 整体时间进度比例（0.0 = 起点，1.0 = 终点）
     * @return 对应时刻的摄像机状态
     */
    public CameraKeyframe evaluate(double u) {
        double clampedU = Math.max(0.0, Math.min(1.0, u));
        if (keyframes.size() == 2) {
            return CameraKeyframe.lerp(keyframes.get(0), keyframes.get(1), clampedU);
        }

        // 将归一化时间换算为目标物理距离
        double targetDist = clampedU * totalLength;

        // 在预计算的弧长表中二分查找对应的原始参数 t
        double rawT = findRawTForDistance(targetDist);
        return evaluateRaw(rawT);
    }

    /**
     * 在累积弧长表中查找距离对应的原始样条参数 rawT。
     */
    private double findRawTForDistance(double targetDist) {
        int n = cumulativeLengths.size();
        if (targetDist <= 0.0) {
            return 0.0;
        }
        if (targetDist >= totalLength) {
            return 1.0;
        }

        // 二分查找
        int low = 0;
        int high = n - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            double midVal = cumulativeLengths.get(mid);
            if (midVal < targetDist) {
                low = mid + 1;
            } else if (midVal > targetDist) {
                high = mid - 1;
            } else {
                return (double) mid / (double) (n - 1);
            }
        }

        // 在 low-1 与 low 之间做细微线性插值
        int idx = Math.max(0, Math.min(n - 2, low - 1));
        double d0 = cumulativeLengths.get(idx);
        double d1 = cumulativeLengths.get(idx + 1);
        double segFraction = (d1 > d0) ? (targetDist - d0) / (d1 - d0) : 0.0;

        return ((double) idx + segFraction) / (double) (n - 1);
    }

    /**
     * 按原始非均匀参数 rawT（0.0 ~ 1.0）对整条曲线进行综合机位插值。
     */
    public CameraKeyframe evaluateRaw(double rawT) {
        int numSegments = keyframes.size() - 1;
        double scaledT = Math.max(0.0, Math.min(1.0, rawT)) * numSegments;
        int segIndex = Math.min((int) scaledT, numSegments - 1);
        double localT = scaledT - segIndex;

        // 确定用于 Catmull-Rom 插值的 4 个控制点 p0, p1, p2, p3
        CameraKeyframe p0 = keyframes.get(Math.max(0, segIndex - 1));
        CameraKeyframe p1 = keyframes.get(segIndex);
        CameraKeyframe p2 = keyframes.get(segIndex + 1);
        CameraKeyframe p3 = keyframes.get(Math.min(keyframes.size() - 1, segIndex + 2));

        // 空间坐标采用 Catmull-Rom 样条平滑
        Vec3 pos = interpolateCatmullRom(p0.position(), p1.position(), p2.position(), p3.position(), localT);

        // 角度、FOV 在 p1 和 p2 之间进行平滑过渡
        CameraKeyframe intermediate = CameraKeyframe.lerp(p1, p2, localT);

        return new CameraKeyframe(pos, intermediate.pitch(), intermediate.yaw(), intermediate.roll(), intermediate.fov());
    }

    /**
     * 仅快速计算原始位置，用于弧长积分预计算。
     */
    private Vec3 evaluateRawPosition(double rawT) {
        int numSegments = keyframes.size() - 1;
        double scaledT = Math.max(0.0, Math.min(1.0, rawT)) * numSegments;
        int segIndex = Math.min((int) scaledT, numSegments - 1);
        double localT = scaledT - segIndex;

        Vec3 p0 = keyframes.get(Math.max(0, segIndex - 1)).position();
        Vec3 p1 = keyframes.get(segIndex).position();
        Vec3 p2 = keyframes.get(segIndex + 1).position();
        Vec3 p3 = keyframes.get(Math.min(keyframes.size() - 1, segIndex + 2)).position();

        return interpolateCatmullRom(p0, p1, p2, p3, localT);
    }

    /**
     * Catmull-Rom 标准插值多项式公式：
     * P(t) = 0.5 * ( (2*P1) + (-P0 + P2)*t + (2*P0 - 5*P1 + 4*P2 - P3)*t^2 + (-P0 + 3*P1 - 3*P2 + P3)*t^3 )
     */
    private static Vec3 interpolateCatmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;

        double c0 = -0.5 * t3 + t2 - 0.5 * t;
        double c1 = 1.5 * t3 - 2.5 * t2 + 1.0;
        double c2 = -1.5 * t3 + 2.0 * t2 + 0.5 * t;
        double c3 = 0.5 * t3 - 0.5 * t2;

        double x = p0.x * c0 + p1.x * c1 + p2.x * c2 + p3.x * c3;
        double y = p0.y * c0 + p1.y * c1 + p2.y * c2 + p3.y * c3;
        double z = p0.z * c0 + p1.z * c1 + p2.z * c2 + p3.z * c3;

        return new Vec3(x, y, z);
    }
}
