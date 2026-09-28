package com.mannequin.entity;

/**
 * 漫剧虚拟替身圆柱体姿态预设（Cylinder Stand-in Pose Presets）。
 *
 * <p>为虚拟制片提供极简干净的几何圆柱替身（Stand-in Proxy），
 * 支持直立站立、平躺地面与压缩下蹲状态，方便导演在场景中快速排位与进行 AIGC 空间语义遮罩。
 */
public enum MannequinPose {
    /** 标准直立站立姿态 */
    STANDING("直立站立"),
    /** 倒地平躺地面姿态 */
    LYING("平躺地面"),
    /** 紧凑压缩下蹲姿态 */
    CROUCHING("压缩下蹲");

    private final String displayName;

    MannequinPose(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static MannequinPose byId(int id) {
        MannequinPose[] values = values();
        if (id < 0 || id >= values.length) {
            return STANDING;
        }
        return values[id];
    }
}
