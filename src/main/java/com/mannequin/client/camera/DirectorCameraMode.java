package com.mannequin.client.camera;

/**
 * 导演相机运行模式。
 */
public enum DirectorCameraMode {

    /**
     * 未开启导演模式：摄像机由 Minecraft 原版玩家控制接管。
     */
    INACTIVE,

    /**
     * 自由飞控漫游模式（Freecam）：
     * 玩家脱离角色身体，以自带物理惯性阻尼的平滑自由视角进行找机位、测构图与贴脸微调。
     */
    FREECAM,

    /**
     * 机械滑轨自动巡航模式（Dolly Playback）：
     * 摄像机严格按照预先打下的 A-B-C 关键帧样条曲线，以绝对恒定的速度自动滑行，供录屏收录高质量漫剧镜头。
     */
    DOLLY_PLAYBACK
}
