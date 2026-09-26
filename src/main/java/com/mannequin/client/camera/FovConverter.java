package com.mannequin.client.camera;

/**
 * 镜头焦距与视角（FOV）换算工具类。
 *
 * <p>在影视摄影中，导演与摄影师习惯以 35mm 全画幅胶片/传感器（画幅尺寸 36mm × 24mm）
 * 的物理焦距（如 24mm 广角、50mm 标准人像、85mm 黄金特写）作为视场角直觉标准。
 *
 * <p>Minecraft 内部的 FOV 属于<b>垂直视场角（Vertical FOV）</b>，计算公式基于全画幅传感器的垂直高度（24mm）：
 * <pre>
 *   焦距 f = 24mm / (2 * tan(垂直FOV_rad / 2))
 *   垂直FOV_rad = 2 * atan(24mm / (2 * f))
 * </pre>
 *
 * <p>本工具类提供双向换算，并为常用经典电影镜头提供语义化焦段标签，辅助漫剧导演精准构图。
 */
public final class FovConverter {

    /**
     * 35mm 全画幅传感器的感光高度（单位：毫米）。
     * 全画幅规格为 36mm × 24mm，垂直 FOV 对应的高度即为 24mm。
     */
    private static final double SENSOR_HEIGHT_MM = 24.0;

    private FovConverter() {
    }

    /**
     * 将 Minecraft 的垂直 FOV（度数）换算为 35mm 全画幅等效焦距（毫米）。
     *
     * @param fovDegrees Minecraft 垂直视角（度数，通常在 10° ~ 130° 之间）
     * @return 35mm 全画幅等效镜头物理焦距（毫米）
     */
    public static double fovToFocalLength(double fovDegrees) {
        double clampedFov = Math.max(1.0, Math.min(170.0, fovDegrees));
        double fovRadians = Math.toRadians(clampedFov);
        return SENSOR_HEIGHT_MM / (2.0 * Math.tan(fovRadians / 2.0));
    }

    /**
     * 将 35mm 等效物理焦距（毫米）换算为 Minecraft 垂直视角 FOV（度数）。
     *
     * @param focalLengthMm 镜头物理焦距（毫米，如 24.0、50.0、85.0）
     * @return 对应的垂直视角 FOV（度数）
     */
    public static double focalLengthToFov(double focalLengthMm) {
        double clampedFocal = Math.max(1.0, focalLengthMm);
        double fovRadians = 2.0 * Math.atan(SENSOR_HEIGHT_MM / (2.0 * clampedFocal));
        return Math.toDegrees(fovRadians);
    }

    /**
     * 根据等效焦距，获取通俗易懂的影视镜头分类描述。
     *
     * @param focalLengthMm 镜头等效焦距（毫米）
     * @return 镜头分类说明（如 "超广角"、"标准人像"、"中长焦特写"）
     */
    public static String getFocalLengthDescription(double focalLengthMm) {
        if (focalLengthMm < 20.0) {
            return "超广角 (鱼眼/大透视)";
        } else if (focalLengthMm < 28.0) {
            return "广角 (大场景环境)";
        } else if (focalLengthMm < 40.0) {
            return "人文纪实 (经典35mm)";
        } else if (focalLengthMm < 65.0) {
            return "标准人像 (自然透视50mm)";
        } else if (focalLengthMm < 105.0) {
            return "黄金人像 (特写85mm)";
        } else if (focalLengthMm < 200.0) {
            return "中长焦 (压缩空间)";
        } else {
            return "超长焦 (望远特写)";
        }
    }
}
