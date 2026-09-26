package com.mannequin.client.camera;

/**
 * 漫剧导演自由相机运镜操控风格（Camera Flight Profile / Style）。
 *
 * <p>提供两种截然不同质感的镜头操控体系：
 * <ul>
 *   <li><b>防抖平稳视角（STABILIZED）</b>：三轴水平防抖，锁定零侧倾晃荡，匀速平稳推进，即走即停，适合稳健构图、推拉摇移与电影对话分镜；</li>
 *   <li><b>穿越机航模视角（FPV_DRONE）</b>：四轴气动侧倾联动，推力加速度与滑翔漂移惯性，适合高速掠地、俯冲穿梭与大动态特技追焦。</li>
 * </ul>
 */
public enum CameraFlightStyle {

    /**
     * 防抖平稳视角（三轴云台/轨道推车稳态物理模式）：
     * 锁定水平防抖零侧倾，消除转向多余倾斜与晃荡，移动响应即走即停，适合稳定机位构图与平稳漫剧运镜。
     */
    STABILIZED("🛡 防抖平稳", "三轴水平防抖 | 零侧倾晃荡 | 即走即停稳态运镜"),

    /**
     * 穿越机航模视角（FPV 四轴动态飞控模式）：
     * 具备转弯气动侧倾倾角、油门推力加速与滑翔漂移惯性，适合高速掠地、俯冲与大动态特技追焦。
     */
    FPV_DRONE("🚁 穿越机", "气动转弯侧倾 | 惯性滑翔漂移 | 高动态飞控运镜");

    private final String displayName;
    private final String description;

    CameraFlightStyle(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public CameraFlightStyle next() {
        return this == STABILIZED ? FPV_DRONE : STABILIZED;
    }
}
